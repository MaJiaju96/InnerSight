package cn.innersight.common.error;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> custom(ApiException ex) {
        return ResponseEntity.status(ex.status).body(Map.of("code", ex.code, "message", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> duplicate() {
        return custom(new ApiException(409, "DUPLICATE", "登录名或角色编码已存在。"));
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<Map<String, String>> unavailable() {
        return custom(new ApiException(503, "UNAVAILABLE", "服务暂不可用，请稍后重试。"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> malformed() {
        return custom(ApiException.invalid("请检查提交内容。"));
    }
}
