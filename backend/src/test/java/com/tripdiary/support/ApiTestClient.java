package com.tripdiary.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 結合テスト用: API でユーザーを登録し、アクセストークンを得る。 */
public final class ApiTestClient {

    private ApiTestClient() {}

    /** 登録済みユーザー（ID とアクセストークン）。 */
    public record TestAccount(long id, String username, String accessToken) {

        public MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder builder) {
            return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
    }

    public static TestAccount signup(MockMvc mockMvc, String username) throws Exception {
        String body =
                """
                {"email":"%s@example.com","username":"%s","displayName":"%s さん","password":"Passw0rd123"}
                """
                        .formatted(username, username, username);
        String response =
                mockMvc.perform(
                                post("/api/v1/auth/signup")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return new TestAccount(
                ((Number) JsonPath.read(response, "$.user.id")).longValue(),
                username,
                JsonPath.read(response, "$.accessToken"));
    }
}
