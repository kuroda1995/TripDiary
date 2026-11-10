package com.tripdiary.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripdiary.common.logging.MdcKeys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    /** 例外を発生させるためだけのテスト用コントローラー。 */
    @RestController
    static class TestController {

        record CreateRequest(
                @NotBlank(message = "タイトルは必須です") @Size(max = 5, message = "タイトルは5文字以内です")
                        String title) {}

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("SPOT_NOT_FOUND", "指定されたスポットは存在しません");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("他人の投稿は編集できません");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("EMAIL_ALREADY_EXISTS", "このメールアドレスは登録済みです");
        }

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException("CANNOT_FOLLOW_SELF", "自分自身はフォローできません");
        }

        @GetMapping("/unauthenticated")
        void unauthenticated() {
            throw new InsufficientAuthenticationException("no token");
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/items/{id}")
        long item(@PathVariable long id) {
            return id;
        }

        @PostMapping("/items")
        void create(@Valid @RequestBody CreateRequest request) {}
    }

    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new TestController())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("NotFoundException は 404 と指定したエラーコードを返す")
    void notFound() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPOT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("指定されたスポットは存在しません"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("ForbiddenException は 403 FORBIDDEN を返す")
    void forbidden() throws Exception {
        mockMvc.perform(get("/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ConflictException は 409 を返す")
    void conflict() throws Exception {
        mockMvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("BadRequestException は 400 と業務エラーコードを返す")
    void badRequest() throws Exception {
        mockMvc.perform(get("/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_FOLLOW_SELF"));
    }

    @Test
    @DisplayName("入力値の検証エラーは 400 VALIDATION_ERROR と項目ごとの詳細を返す")
    void validationError() throws Exception {
        mockMvc.perform(
                        post("/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("title"))
                .andExpect(jsonPath("$.details[0].message").value("タイトルは必須です"));
    }

    @Test
    @DisplayName("JSON として読めないリクエストボディは 400 VALIDATION_ERROR を返す")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("パスパラメータの型不一致は 400 VALIDATION_ERROR を返す")
    void typeMismatch() throws Exception {
        mockMvc.perform(get("/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("許可されていない HTTP メソッドは 405 を返す")
    void methodNotAllowed() throws Exception {
        mockMvc.perform(post("/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("認証エラーは 401 UNAUTHORIZED を返す")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("認可エラーは 403 FORBIDDEN を返す")
    void accessDenied() throws Exception {
        mockMvc.perform(get("/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("予期しない例外は 500 INTERNAL_ERROR を返し、内部の詳細をレスポンスに含めない")
    void unexpectedError() throws Exception {
        MDC.put(MdcKeys.REQUEST_ID, "req-500");

        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        org.hamcrest.Matchers.not(
                                                org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(jsonPath("$.requestId").value("req-500"));
    }
}
