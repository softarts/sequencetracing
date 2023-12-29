package com.zhourui.tracing;

import java.util.function.Consumer;

public abstract class BaseConsumer<SELF extends BaseConsumer<SELF>> implements Consumer<OutputFrame> {
    private boolean removeColorCodes = true;

    public boolean isRemoveColorCodes() {
        return removeColorCodes;
    }

    public SELF withRemoveAnsiCodes(boolean removeAnsiCodes) {
        this.removeColorCodes = removeAnsiCodes;
        return (SELF) this;
    }
}