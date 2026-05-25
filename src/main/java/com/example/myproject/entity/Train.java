package com.example.myproject.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Train {
    private Long id;
    private String trainNo;
    private String departure;
    private String destination;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer totalSeats;
    private Integer availableSeats;
    private BigDecimal price;
    private Integer status; // 1=可售 0=停售

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTrainNo() { return trainNo; }
    public void setTrainNo(String trainNo) { this.trainNo = trainNo; }

    public String getDeparture() { return departure; }
    public void setDeparture(String departure) { this.departure = departure; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public LocalDateTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalDateTime departureTime) { this.departureTime = departureTime; }

    public LocalDateTime getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(LocalDateTime arrivalTime) { this.arrivalTime = arrivalTime; }

    public Integer getTotalSeats() { return totalSeats; }
    public void setTotalSeats(Integer totalSeats) { this.totalSeats = totalSeats; }

    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
