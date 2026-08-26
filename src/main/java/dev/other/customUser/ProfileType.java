package dev.other.customUser;

import dev.other.customUser.type.SubscriptionType;

import java.util.Calendar;
import java.util.Date;

/*
 * *****************************************************************************
 * *                                                                         * *
 * *                             author : NullCry                            * *
 * *            Пишу в надежде того, что этот код никто не увидит...         * *
 * *                             04.06.2025                                  * *
 * *                                                                         * *
 * *****************************************************************************
 */

public enum ProfileType {
    Developer {
        @Override
        public UserProfile createProfile() {
            return UserProfile.create(getCustomIssueDate(), SubscriptionType.ultimate);
        }
    };

    public abstract UserProfile createProfile();

    public Date getCustomIssueDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, Calendar.NOVEMBER, 25, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
