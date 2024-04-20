package com.gskart.product.security.models;

import lombok.Data;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@Data
public class GSKartResourceServerUserContext {

    private GSKartResourceServerUser gskartResourceServerUser;

//    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if(username.equals(gskartResourceServerUser.getUsername())) {
            return gskartResourceServerUser;
        }
        return null;
    }
}
