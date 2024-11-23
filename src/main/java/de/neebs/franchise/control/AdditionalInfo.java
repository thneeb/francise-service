package de.neebs.franchise.control;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
public class AdditionalInfo {
    private List<String> influenceComments;
    private int income;
    private int money;
}
