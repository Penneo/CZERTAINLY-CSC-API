package com.czertainly.csc.providers;

import com.czertainly.csc.common.exceptions.ApplicationConfigurationException;
import com.czertainly.csc.common.PatternReplacer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class PatternDnProvider implements DistinguishedNameProvider {

    private final PatternReplacer patternReplacer;

    public PatternDnProvider(@Value("${caProvider.ejbca.endEntity.dnPattern}") String dnPattern) {
        if (dnPattern.isBlank()) {
            throw new ApplicationConfigurationException("Distinguished Name pattern is not set.");
        }
        this.patternReplacer = new PatternReplacer(dnPattern, "Distinguished Name Provider");;
    }
    @Override
    public String getDistinguishedName(Supplier<Map<String, String>> keyValueSource) {
        return patternReplacer.replacePattern(keyValueSource);
    }
}
