package com.czertainly.csc.signing;

import com.czertainly.csc.clients.idp.IdpClient;
import com.czertainly.csc.model.UserInfo;
import org.springframework.stereotype.Component;

@Component
public class IdpUserInfoProvider implements UserInfoProvider {

    IdpClient idpClient;

    public IdpUserInfoProvider(IdpClient idpClient) {
        this.idpClient = idpClient;
    }

    @Override
    public UserInfo getUserInfo(String token) {
        try {
            return idpClient.downloadUserInfo(token);
        } catch (Exception e) {
            return null;
        }
    }

}
