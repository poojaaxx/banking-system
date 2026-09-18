package com.bankingdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final AdminBootstrap adminBootstrap = new AdminBootstrap();
    private final DemoLimits demoLimits = new DemoLimits();
    private final RateLimit rateLimit = new RateLimit();
    private boolean trustProxyHeaders = false;

    public AdminBootstrap getAdminBootstrap() {
        return adminBootstrap;
    }

    public DemoLimits getDemoLimits() {
        return demoLimits;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public boolean isTrustProxyHeaders() {
        return trustProxyHeaders;
    }

    public void setTrustProxyHeaders(boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    public static class AdminBootstrap {
        private String username;
        private String email;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class DemoLimits {
        private BigDecimal maxFundingAmount = new BigDecimal("100000");
        private BigDecimal maxTransferAmount = new BigDecimal("100000");
        private BigDecimal dailyTransferLimit = new BigDecimal("500000");

        public BigDecimal getMaxFundingAmount() {
            return maxFundingAmount;
        }

        public void setMaxFundingAmount(BigDecimal maxFundingAmount) {
            this.maxFundingAmount = maxFundingAmount;
        }

        public BigDecimal getMaxTransferAmount() {
            return maxTransferAmount;
        }

        public void setMaxTransferAmount(BigDecimal maxTransferAmount) {
            this.maxTransferAmount = maxTransferAmount;
        }

        public BigDecimal getDailyTransferLimit() {
            return dailyTransferLimit;
        }

        public void setDailyTransferLimit(BigDecimal dailyTransferLimit) {
            this.dailyTransferLimit = dailyTransferLimit;
        }
    }

    public static class RateLimit {
        private int loginPerMinute = 10;
        private int registerPerHour = 20;
        private int recoveryPerHour = 10;
        private int recipientLookupPerMinute = 30;
        private int fundingPerMinute = 10;

        public int getLoginPerMinute() {
            return loginPerMinute;
        }

        public void setLoginPerMinute(int loginPerMinute) {
            this.loginPerMinute = loginPerMinute;
        }

        public int getRegisterPerHour() {
            return registerPerHour;
        }

        public void setRegisterPerHour(int registerPerHour) {
            this.registerPerHour = registerPerHour;
        }

        public int getRecoveryPerHour() {
            return recoveryPerHour;
        }

        public void setRecoveryPerHour(int recoveryPerHour) {
            this.recoveryPerHour = recoveryPerHour;
        }

        public int getRecipientLookupPerMinute() {
            return recipientLookupPerMinute;
        }

        public void setRecipientLookupPerMinute(int recipientLookupPerMinute) {
            this.recipientLookupPerMinute = recipientLookupPerMinute;
        }

        public int getFundingPerMinute() {
            return fundingPerMinute;
        }

        public void setFundingPerMinute(int fundingPerMinute) {
            this.fundingPerMinute = fundingPerMinute;
        }
    }
}
