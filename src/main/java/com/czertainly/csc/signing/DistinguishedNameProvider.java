package com.czertainly.csc.signing;

import com.czertainly.csc.model.UserInfo;

public interface DistinguishedNameProvider {

    String getDistinguishedName(UserInfo userInfo);
}
