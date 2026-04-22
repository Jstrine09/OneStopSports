package com.onestopsports.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "league")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class League {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sport_id", nullable = false)
    private Sport sport;

    /** football-data.org competition ID — e.g. 2021 = Premier League */
    private Integer externalId;

    @Column(nullable = false)
    private String name;

    private String country;
    private String logoUrl;
    private String season;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
