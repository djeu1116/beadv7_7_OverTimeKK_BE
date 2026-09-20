package com.programmers.kdt.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// 주문/결제를 서비스로 분리하기 위한 경계 고정. 두 도메인은 서로를 직접 참조하지 않고,
// 양쪽을 아는 코드는 중립 어댑터 패키지 하나뿐이어야 한다.
class PackageDependencyTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/programmers/kdt");

    @Test
    @DisplayName("결제 패키지는 주문 패키지를 참조하지 않는다.")
    void paymentDoesNotDependOnOrder() {
        assertThat(violations("payment", "com.programmers.kdt.order")).isEmpty();
    }

    @Test
    @DisplayName("주문 패키지는 결제 패키지를 참조하지 않는다.")
    void orderDoesNotDependOnPayment() {
        assertThat(violations("order", "com.programmers.kdt.payment")).isEmpty();
    }

    private List<String> violations(String packageName, String forbiddenPrefix) {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT.resolve(packageName))) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> readContent(path).contains(forbiddenPrefix))
                    .map(SOURCE_ROOT::relativize)
                    .map(Path::toString)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String readContent(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
