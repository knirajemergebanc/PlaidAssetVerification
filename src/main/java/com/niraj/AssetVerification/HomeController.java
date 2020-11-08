package com.niraj.AssetVerification;

import com.plaid.client.PlaidClient;
import com.plaid.client.request.*;
import com.plaid.client.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Controller
public class HomeController {

    private final Environment env;
    private final PlaidClient plaidClient;
    private final PlaidAuthService authService;


    @Autowired
    public HomeController(Environment env, PlaidClient plaidClient, PlaidAuthService authService) {
        this.env = env;
        this.plaidClient = plaidClient;
        this.authService = authService;
    }


    /**
     * Home page.
     */
    @RequestMapping(value="/", method=GET)
    public String index(Model model) {
        model.addAttribute("PLAID_PUBLIC_KEY", env.getProperty("PLAID_PUBLIC_KEY"));
        model.addAttribute("PLAID_ENV", env.getProperty("PLAID_ENV"));
        return "index";
    }

    @RequestMapping(value = "/get_link_token")
    public @ResponseBody ResponseEntity get_link_token() throws IOException {
        LinkTokenCreateRequest.User user = new LinkTokenCreateRequest.User("5f2d71116f6fee0012b26129");
        Response<LinkTokenCreateResponse> response = this.plaidClient.service()
                .linkTokenCreate(new LinkTokenCreateRequest(
                        user,
                        "My App",
                        Collections.singletonList("transactions"),
                        Collections.singletonList("US"),
                        "en"
                )).execute();
        if(response.isSuccessful()) {
            Map<String, Object> data = new HashMap<>();
            data.put("public_token", response.body().getLinkToken());
            return ResponseEntity.ok(data);
        } else return ResponseEntity.status(500).body(response.errorBody().toString());
    }

    /**
     * Exchange link public token for access token.
     */
    @RequestMapping(value="/get_access_token", method=POST, consumes= MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public @ResponseBody
    ResponseEntity getAccessToken(@RequestParam("public_token") String publicToken) throws Exception {
        Response<ItemPublicTokenExchangeResponse> response = this.plaidClient.service()
                .itemPublicTokenExchange(new ItemPublicTokenExchangeRequest(publicToken))
                .execute();

        if (response.isSuccessful()) {
            this.authService.setAccessToken(response.body().getAccessToken());
            this.authService.setItemId(response.body().getItemId());

            Map<String, Object> data = new HashMap<>();
            data.put("error", false);

            return ResponseEntity.ok(data);
        } else {
            return ResponseEntity.status(500).body(getErrorResponseData(response.errorBody().string()));
        }
    }

    /**
     * Retrieve high-level account information and account and routing numbers
     * for each account associated with the Item.
     */
    @RequestMapping(value="/accounts", method=GET, produces=MediaType.APPLICATION_JSON_VALUE)
    public @ResponseBody ResponseEntity getAccount() throws Exception {
        if (authService.getAccessToken() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(getErrorResponseData("Not authorized"));
        }

        Response<AuthGetResponse> response = this.plaidClient.service()
                .authGet(new AuthGetRequest(this.authService.getAccessToken())).execute();

        if (response.isSuccessful()) {
            Map<String, Object> data = new HashMap<>();
            data.put("error", false);
            data.put("accounts", response.body().getAccounts());
            data.put("numbers", response.body().getNumbers());

            return ResponseEntity.ok(data);
        } else {
            Map<String, Object> data = new HashMap<>();
            data.put("error", "Unable to pull accounts from the Plaid API.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(data);
        }
    }


    
    private Map<String, Object> getErrorResponseData(String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", false);
        data.put("message", message);
        return data;
    }
}
