package dev.other.customUser;

import dev.other.customUser.type.RoleType;
import dev.other.customUser.type.SubscriptionType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Getter
public class UserProfile {
    private final String user;
    private final int uid;
    private final RoleType roleType;
    private final Date issueDate;
    private final Date expireDate;
    private final SubscriptionType subscriptionType;

    public UserProfile(int uid, String user, RoleType roleType, Date issueDate, Date expireDate, SubscriptionType subscriptionType) {
        this.uid = uid;
        this.user = user;
        this.roleType = roleType;
        this.issueDate = issueDate;
        this.expireDate = expireDate;
        this.subscriptionType = subscriptionType;
    }

    public static UserProfile create(Date issueDate, SubscriptionType type) {
        return new UserProfile(
                0,
                "Anon1mysxd",
                RoleType.Developer,
                issueDate,
                calculateExpireDate(issueDate, type),
                type
        );
    }

    private static Date calculateExpireDate(Date issueDate, SubscriptionType type) {
        if (type == null || type == SubscriptionType.None) {
            return null;
        }

        LocalDateTime localDateTime = issueDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();

        LocalDateTime expire = switch (type) {
            case one_month -> localDateTime.plusDays(30);
            case three_month -> localDateTime.plusDays(90);
            case one_year -> localDateTime.plusDays(365);
            case ultimate -> localDateTime.plusYears(10);
            default -> localDateTime;
        };

        return Date.from(expire.atZone(ZoneId.systemDefault()).toInstant());
    }
}
