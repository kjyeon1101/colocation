package com.sds.colocation.customer;

public enum CustomerTier {
    STANDARD("일반"),
    ENTERPRISE("엔터프라이즈");

    private final String label;

    CustomerTier(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
