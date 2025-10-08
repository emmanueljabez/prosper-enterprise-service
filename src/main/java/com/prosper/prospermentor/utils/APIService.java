package com.prosper.prospermentor.utils;

import com.prosper.prospermentor.model.SafaricomToken;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;

public interface APIService {
    @GET("generate?grant_type=client_credentials")
    Call<SafaricomToken> getToken(@Header("Authorization") String authorization);
}
