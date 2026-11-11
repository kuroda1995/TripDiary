package com.tripdiary.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

/**
 * PATCH 用: 項目を送った場合は null・空白のみを許さない（送らなければ検証しない）。
 *
 * <p>{@code Optional<@NotBlank String>} だと、項目を送らなかった場合（フィールドが null）も中身を null として検証してしまうため、 {@link
 * Optional} そのものを検証する。
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotBlankIfPresent.Validator.class)
public @interface NotBlankIfPresent {

    String message() default "入力してください";

    Class<?>[] groups() default {};

    /**
     * 使う側で {@code payload = Unwrapping.Skip.class} を指定し、Optional の中身ではなく Optional 自体に適用すること
     * （仕様上、既定値は空配列でなければならない）。
     */
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<NotBlankIfPresent, Optional<String>> {

        @Override
        public boolean isValid(Optional<String> value, ConstraintValidatorContext context) {
            return value == null || value.filter(s -> !s.isBlank()).isPresent();
        }
    }
}
