package com.ecommerce.backend.dto;

import lombok.Data;

@Data
public class AdminDashboardDto {
    private long totalItems;
    private long lowStockItems;
    private long activeDiscounts;
    
    private java.util.List<RevenuePoint> revenueData;

    @Data
    public static class RevenuePoint {
        private String label;
        private double revenue;
        
        public RevenuePoint(String label, double revenue) {
            this.label = label;
            this.revenue = revenue;
        }
    }
}
