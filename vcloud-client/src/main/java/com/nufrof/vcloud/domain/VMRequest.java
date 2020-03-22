package com.nufrof.vcloud.domain;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

import javax.validation.constraints.Size;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@RequiredArgsConstructor
public class VMRequest {
    private static final AtomicInteger atomicInteger = new AtomicInteger();
    private final Integer id = atomicInteger.getAndIncrement();
    private final String random = RandomStringUtils.randomAlphanumeric(3);
    @NonNull
    private final String catalog;
    @NonNull
    private final String vApp;
    @NonNull
    private final String vm;
    @NonNull
    @Size(min = 1, max = 3)
    private final String name;
    @NonNull
    private final Integer numCpus;
    @NonNull
    private final Integer mbsMemory;

    public String getUniqueName() {
        return name + random + id;
    }
}
