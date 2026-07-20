package com.invsys.support;

import java.util.List;

record HeuristicSupportResult(String answer, List<SupportActionProposal> actions) {
    static HeuristicSupportResult of(String answer) {
        return new HeuristicSupportResult(answer, List.of());
    }

    static HeuristicSupportResult of(String answer, List<SupportActionProposal> actions) {
        return new HeuristicSupportResult(answer, actions == null ? List.of() : List.copyOf(actions));
    }
}
