package com.fixhomi.auth.dto;

import java.util.List;

public class AdminUserListResponse {
    private List<UserProfileResponse> users;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public AdminUserListResponse() {}

    public AdminUserListResponse(List<UserProfileResponse> users, int page, int size, long totalElements, int totalPages) {
        this.users = users;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    // Getters and Setters
    public List<UserProfileResponse> getUsers() { return users; }
    public void setUsers(List<UserProfileResponse> users) { this.users = users; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
