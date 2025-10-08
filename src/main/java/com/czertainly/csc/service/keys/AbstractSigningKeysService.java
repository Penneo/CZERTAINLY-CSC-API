package com.czertainly.csc.service.keys;

import com.czertainly.csc.clients.signserver.SignserverClient;
import com.czertainly.csc.common.result.Result;
import com.czertainly.csc.common.result.TextError;
import com.czertainly.csc.model.signserver.CryptoToken;
import com.czertainly.csc.repository.KeyRepository;
import com.czertainly.csc.repository.entities.KeyEntity;
import com.czertainly.csc.signing.configuration.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Allows to generate new signing keys on Signserver and stores them in database.
 * Also allows to acquire the key for signature.
 */
@Service
public abstract class AbstractSigningKeysService<E extends KeyEntity, K extends SigningKey> implements KeysService<K> {

    private static final Logger logger = LoggerFactory.getLogger(AbstractSigningKeysService.class);
    protected final KeyRepository<E> keysRepository;
    private final SignserverClient signserverClient;
    protected final WorkerRepository workerRepository;
    private final TransactionTemplate transactionTemplate;

    // ReentrantLock per crypto token ID to prevent duplicate key generation while allowing virtual threads to unmount
    private static final ConcurrentHashMap<Integer, ReentrantLock> cryptoTokenLocks = new ConcurrentHashMap<>();


    public AbstractSigningKeysService(KeyRepository<E> keysRepository, SignserverClient signserverClient,
                                      WorkerRepository workerRepository, TransactionTemplate transactionTemplate
    ) {
        this.keysRepository = keysRepository;
        this.signserverClient = signserverClient;
        this.workerRepository = workerRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Result<Integer, TextError> getNumberOfUsableKeys(CryptoToken cryptoToken, String keyAlgorithm) {
        logger.debug("Counting free keys of CryptoToken '{}' with key algorithm '{}'", cryptoToken.identifier(),
                     keyAlgorithm
        );
        try {
            int numOfFreeKeys = keysRepository.countByCryptoTokenIdAndKeyAlgorithmAndInUse(
                    cryptoToken.id(), keyAlgorithm, false
            );
            return Result.success(numOfFreeKeys);
        } catch (Exception e) {
            logger.error("Couldn't count number of free keys of CryptoToken '{}' with key algorithm '{}'.",
                         cryptoToken.identifier(), keyAlgorithm, e
            );
            return Result.error(TextError.of(
                    "Couldn't count number of free keys of CryptoToken '%s'.",
                    cryptoToken.identifier()
            ));
        }
    }

    private Result<K, TextError> generateKey(CryptoToken cryptoToken, String keyAlgorithm) {
        // Select the first KeyPoolProfile that matches the key algorithm.
        // If no KeyPoolProfile is found, return Result with an error.
        return cryptoToken.keyPoolProfiles().stream()
                .filter(kpp -> kpp.keyAlgorithm().equals(keyAlgorithm))
                .findFirst()
                .map(kpp -> {
                    String keyAlias = kpp.keyPrefix() + "-" + UUID.randomUUID();
                    return generateKey(cryptoToken, keyAlias, keyAlgorithm, kpp.keySpecification());
                })
                .orElseGet(() -> Result.error(TextError.of(
                        "No KeyPoolProfile found for key algorithm '%s' in CryptoToken '%s'.",
                                keyAlgorithm, cryptoToken.identifier())));
    }

    @Override
    public Result<K, TextError> generateKey(
            CryptoToken cryptoToken, String keyAlias, String keyAlgorithm, String keySpec
    ) {
        logger.debug("Generating a new key for CryptoToken '{}' with alias '{}', algorithm '{}' and key spec '{}'",
                     cryptoToken.identifier(), keyAlias, keyAlgorithm, keySpec
        );
        return signserverClient.generateKey(cryptoToken, keyAlias, keyAlgorithm, keySpec)
                               .mapError(e -> e.extend(
                                       "Key couldn't be generated on Signserver CryptoToken '%s'.",
                                       cryptoToken.identifier()
                               ))
                               .flatMap(finalKeyAlias -> saveKey(cryptoToken, finalKeyAlias, keyAlgorithm))
                               .map(keyEntity -> this.mapEntityToSigningKey(keyEntity, cryptoToken));
    }

    @Override
    public Result<K, TextError> acquireKey(CryptoToken cryptoToken, String keyAlgorithm) {
        logger.debug("Acquiring a signing key of CryptoToken '{}' with algorithm '{}'",
                     cryptoToken.identifier(), keyAlgorithm
        );
        // Use ReentrantLock instead of synchronized to allow virtual threads to unmount during blocking I/O
        ReentrantLock lock = cryptoTokenLocks.computeIfAbsent(cryptoToken.id(), id -> new ReentrantLock());

        lock.lock();
        try {
            // Execute database operations inside a transaction, but only after acquiring the lock
            // This ensures threads wait for the lock WITHOUT holding a database connection
            return transactionTemplate.execute(status -> {
                Optional<E> existingKey = keysRepository.findFirstByCryptoTokenIdAndKeyAlgorithmAndInUse(
                        cryptoToken.id(), keyAlgorithm, false
                );

                if (existingKey.isPresent()) {
                    E entity = existingKey.get();
                    entity.setAcquiredAt(ZonedDateTime.now());
                    entity.setInUse(true);
                    E savedEntity = keysRepository.save(entity);
                    K key = this.mapEntityToSigningKey(savedEntity, cryptoToken);
                    return Result.success(key);
                } else {
                    // If no key is found, generate a new one outside the transaction
                    // Key generation involves calling SignServer API (slow I/O operation)
                    status.setRollbackOnly();
                    return Result.error(TextError.of("No available key found"));
                }
            });
        } catch (Exception ex) {
            // If no key was found, generate a new one
            Result<K, TextError> generateResult = generateKey(cryptoToken, keyAlgorithm);
            if (generateResult instanceof com.czertainly.csc.common.result.Error(var err)) {
                return Result.error(err.extend(
                        "New key couldn't be acquired from CryptoToken '%s'.",
                        cryptoToken.identifier()
                ));
            }

            K generatedKey = generateResult.unwrap();

            // Mark the newly generated key as acquired in a separate transaction
            return transactionTemplate.execute(status -> {
                keysRepository.findById(generatedKey.id()).ifPresent(entity -> {
                    entity.setAcquiredAt(ZonedDateTime.now());
                    entity.setInUse(true);
                    keysRepository.save(entity);
                });
                return Result.success(generatedKey);
            });
        } finally {
            lock.unlock();
        }
    }

    public Result<K, TextError> getKey(UUID keyId) {
        logger.debug("Obtaining signing key with id {}", keyId);
        Optional<E> keyEntity = keysRepository.findById(keyId);

        if (keyEntity.isPresent()) {
            return workerRepository.getCryptoToken(keyEntity.get().getCryptoTokenId())
                                   .map(cryptoToken -> this.mapEntityToSigningKey(keyEntity.get(),
                                                                                  cryptoToken
                                   ))
                                   .mapError(e -> e.extend("Can't retrieve key with id '%s'.", keyId));
        } else {
            return Result.error(TextError.of("Signing key with id '%s' does not exist.", keyId));
        }
    }

    public Result<Void, TextError> deleteKey(UUID keyId) {
        return getKey(keyId).flatMap(this::deleteKey)
                            .mapError(e -> e.extend("Can't delete key with id '%s'.", keyId));
    }

    @Override
    public Result<Void, TextError> deleteKey(K key) {
        logger.debug("Deleting key '{}' with id '{}'", key.keyAlias(), key.id());
        try {
            return signserverClient.removeKeyOkIfNotExists(key.cryptoToken().id(), key.keyAlias())
                                   .flatMap(v -> {
                                       try {
                                           keysRepository.deleteById(key.id());
                                       } catch (Exception e) {
                                           logger.error("Failed to delete signing key '{}' with id '{}' from database.",
                                                        key.keyAlias(), key.id(), e
                                           );
                                           Result.error(TextError.of("Key '%s' not deleted from database.", key.keyAlias()));
                                       }
                                       return Result.emptySuccess();
                                   })
                                   .mapError(e -> e.extend("Key '%s' with id '%s' couldn't be deleted.", key.keyAlias(),
                                                           key.id()
                                   ));
        } catch (Exception e) {
            logger.error("Key '{}' with id '{}' couldn't be deleted.", key.keyAlias(), key.id(), e);
            return Result.error(TextError.of("Key '%s' with id '%s' couldn't be deleted.", key.keyAlias(), key.id()));
        }
    }

    private Result<E, TextError> saveKey(CryptoToken cryptoToken, String keyAlias, String keyAlgorithm) {
        E newEntity = createNewKeyEntity(cryptoToken, keyAlias, keyAlgorithm);

        try {
            logger.debug("Saving new session key '{}' to the database.", newEntity.getId());
            logger.trace("Session key: {}", newEntity);
            E savedEntity = keysRepository.save(newEntity);
            logger.info("New session key '{}' was saved to the database.", savedEntity.getId());
            return Result.success(savedEntity);
        } catch (Exception e) {
            logger.error("SessionKey couldn't be saved to the database.", e);
            return Result.error(new TextError("Key couldn't be saved to the database."));
        }
    }

    public abstract K mapEntityToSigningKey(E entity, CryptoToken cryptoToken);

    public abstract E createNewKeyEntity(CryptoToken cryptoToken, String keyAlias, String keyAlgorithm);
}
