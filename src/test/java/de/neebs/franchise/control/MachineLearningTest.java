package de.neebs.franchise.control;

import lombok.extern.log4j.Log4j;
import org.junit.jupiter.api.Test;

@Log4j
class MachineLearningTest {
    @Test
    void test() {
        MachineLearningUtil util = new MachineLearningUtil();
        FranchiseMLService service = new FranchiseMLService(new FranchiseCoreService(), null);
        log.info(String.join(",", service.createHeader()));
    }
}