package com.nufrof.vcloud.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.List;

@Builder
@Getter
public class VAppRequest {
    @NonNull
    private String org;
    @NonNull
    private String network;
    @Singular
    @Valid
    @NonNull
    private List<VMRequest> vms;
    @NonNull
    @Size(min = 1, max = 8)
    private String name;
}
