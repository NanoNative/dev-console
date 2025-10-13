package org.nanonative.devconsole.util;

import org.nanonative.nano.core.model.Service;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ServiceFactory {
    private final Map<String, ClassInfo> registry = new ConcurrentHashMap<>();
    private final MethodHandles.Lookup lookup = MethodHandles.publicLookup();

    public ServiceFactory(List<String> fqcnList) {
        this(fqcnList, Thread.currentThread().getContextClassLoader());
    }

    public <T extends Service> T newInstance(String name, Class<T> type) {
        return type.cast(newInstance(name));
    }

    public ClassInfo getClassInfo(String simpleName) {
        return registry.get(simpleName);
    }

    private ServiceFactory(List<String> fqcnList, ClassLoader cl) {
        try {
            for (String fqcn : fqcnList) {
                Class<? extends Service> clazz = cl.loadClass(fqcn).asSubclass(Service.class);
                registry.putIfAbsent(getSimpleName(fqcn), new ClassInfo(clazz, fqcn, lazyInitializer(clazz)));
            }
        } catch (ClassNotFoundException cfe) {
            throw new RuntimeException("services.properties has FQDNs which are no longer present: " + cfe);
        }
    }

    private Object newInstance(String name) {
        Supplier<?> sup = registry.containsKey(name) ? registry.get(name).initialize() : null;
        if (null == sup)
            throw new RuntimeException("Unknown service: " + name);
        return sup.get();
    }

    protected Supplier<?> lazyInitializer(Class<? extends Service> clazz) {
        return new Supplier<>() {
            volatile MethodHandle constructor;

            @Override
            public Object get() {
                try {
                    if (null == constructor) {
                        synchronized (this) {
                            constructor = lookup.findConstructor(clazz, MethodType.methodType(void.class));
                        }
                    }
                    return constructor.invoke();
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to instantiate: " + clazz.getSimpleName());
                }
            }
        };
    }

    public static String getSimpleName(String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }
}
