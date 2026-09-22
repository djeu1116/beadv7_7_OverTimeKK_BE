package com.programmers.kdt.payment.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PointErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "point")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Point extends BaseTimeEntity {

    @Id
    private Long userId;

    @Column(name = "total_point", nullable = false)
    private Long totalPoint;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Point create(Long userId) {
        if (userId == null) {
            throw new BusinessException(PointErrorCode.MISSING_USER_ID);
        }
        Point point = new Point();
        point.userId = userId;
        point.totalPoint = 0L; // 초기 포인트는 0원
        return point;
    }

    // 포인트 적립
    public void earn(Long amount) {
        add(amount, "적립");
    }

    // 포인트 사용
    public void use(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(PointErrorCode.ZERO_POINT_AMOUNT, amount);
        }
        if (this.totalPoint < amount) {
            throw new BusinessException(PointErrorCode.INSUFFICIENT_POINT, this.totalPoint, amount);
        }


        this.totalPoint -= amount;
    }

    // 포인트 환급
    public void rollbackUse(Long amount) {
        add(amount, "환급");
    }

    // 지급, 환급시 공통 메서드
    private void add(Long amount, String label) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(PointErrorCode.ZERO_POINT_AMOUNT, amount);
        }
        this.totalPoint += amount;
    }
}
