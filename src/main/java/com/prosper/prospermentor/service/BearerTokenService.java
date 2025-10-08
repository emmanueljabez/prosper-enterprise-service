package com.prosper.prospermentor.service;

import com.google.common.collect.Iterables;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.prosper.prospermentor.entity.Tokens;
import com.prosper.prospermentor.repository.TokenRepositories;
import com.prosper.prospermentor.model.SafaricomToken;
import com.prosper.prospermentor.utils.APIService;
import com.prosper.prospermentor.utils.ApiUtils;
import okhttp3.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

@Service
public class BearerTokenService {
    APIService apiService;
    Logger logger = LoggerFactory.getLogger(BearerTokenService.class);
    private final TokenRepositories tokenRepositories;

    @Value("${mpesa.consumer.key:}")
    private String consumerKey;

    @Value("${mpesa.consumer.secret:}")
    private String consumerSecret;

    public BearerTokenService(TokenRepositories tokenRepositories) {
        this.tokenRepositories = tokenRepositories;
    }

    @Scheduled(
            cron = "0 0/2 * * * *"
    )
    public void autoSetOauthBearerCode() {
        this.logger.error("cron working");

        try {
            this.apiService = ApiUtils.getSafaricomAPIService();
            String credentials = consumerKey + ":" + consumerSecret;
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
            this.apiService.getToken(basicAuth).enqueue(new Callback<SafaricomToken>() {
                public void onResponse(Call<SafaricomToken> call, Response<SafaricomToken> response) {
                    BearerTokenService.this.logger.error("Response stub");
                    if (response.isSuccessful()) {
                        BearerTokenService.this.tokenRepositories.deleteAll();
                        SafaricomToken safaricomToken = (SafaricomToken)response.body();
                        BearerTokenService.this.tokenRepositories.save(new Tokens(safaricomToken.getAccess_token()));
                    } else {
                        BearerTokenService.this.logger.error("Not successfull");
                    }

                }

                public void onFailure(Call<SafaricomToken> call, Throwable t) {
                    BearerTokenService.this.logger.error("Failed attempt");
                }
            });
        } catch (Exception var2) {
            Exception e = var2;
            this.logger.error(e.getMessage());
        }

    }

    public Tokens getToken() {
        Iterable<Tokens> tokens = this.tokenRepositories.findAll();
        this.logger.error("" + Iterables.size(tokens));
        if (Iterables.size(tokens) > 0) {
            Tokens token = null;
            Iterator var3 = tokens.iterator();
            if (var3.hasNext()) {
                Tokens tokenTemp = (Tokens)var3.next();
                token = tokenTemp;
            }

            return token;
        } else {
            return null;
        }
    }

}
