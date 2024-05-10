package com.czertainly.csc.signing;

import com.czertainly.csc.model.UserInfo;

public interface UserInfoProvider {

    UserInfo getUserInfo(String identifier);

}
