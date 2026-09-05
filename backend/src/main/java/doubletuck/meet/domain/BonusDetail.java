package doubletuck.meet.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BonusDetail {

    @JsonProperty("bonus")
    private Double bonus;

    @JsonProperty("description")
    private String description;
}
