package by.northdakota.markettracker.Core.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table(name="price_history")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PriceHistory {

    @Id
    @SequenceGenerator(
            name="price_history_seq",sequenceName = "price_history_sequence",
            initialValue=1,allocationSize=20)
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "price_history_seq")
    private Long id;
    @ManyToOne
    @JoinColumn(name="item_id",nullable=false)
    private TrackedItem item;
    private BigDecimal price;
    private LocalDateTime timestamp;

}
