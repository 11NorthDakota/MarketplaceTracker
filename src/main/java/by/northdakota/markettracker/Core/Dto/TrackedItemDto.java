package by.northdakota.markettracker.Core.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TrackedItemDto {
    private String article;
    private String title;
    private BigDecimal currentPrice;
    private BigDecimal basicPrice;
}
