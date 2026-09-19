package com.ria.olita.tech.silingan.service;


import com.ria.olita.tech.silingan.dto.req.CreateUserRequest;
import com.ria.olita.tech.silingan.dto.res.CreatedUserResponse;

public interface UserService {

	void createUser(CreateUserRequest createUserRequest);

	CreatedUserResponse createSelfServiceUser(CreateUserRequest createUserRequest);

	void updateUserSelectedCommunity(String mobileNumber, String communityCode);
}
