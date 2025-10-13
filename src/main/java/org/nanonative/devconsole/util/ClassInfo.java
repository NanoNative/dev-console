package org.nanonative.devconsole.util;

import org.nanonative.nano.core.model.Service;

import java.util.function.Supplier;

public record ClassInfo(Class<? extends Service> clazz, String fqcn, Supplier<?> initialize) {}
