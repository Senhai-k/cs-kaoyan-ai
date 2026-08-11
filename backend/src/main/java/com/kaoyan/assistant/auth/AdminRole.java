package com.kaoyan.assistant.auth;

public enum AdminRole {
    ADMIN,
    DATA_EDITOR,
    AUDITOR;

    public boolean allows(AdminPermission permission) {
        return switch (this) {
            case ADMIN -> true;
            case DATA_EDITOR -> permission != AdminPermission.ADMIN;
            case AUDITOR -> permission == AdminPermission.READ;
        };
    }
}
