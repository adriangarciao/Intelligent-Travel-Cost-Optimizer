package com.adriangarciao.traveloptimizer.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.DockerClientFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class CloseSpringContextExtension implements AfterAllCallback {

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // Close any ConfigurableApplicationContext found on test instances
        try {
            context.getTestInstances().ifPresent(instances -> instances.getAllInstances().forEach(this::closeContextFromInstance));
        } catch (Throwable ignored) {
        }

        // Also inspect static fields on the test class
        Class<?> testClass = context.getRequiredTestClass();
        for (Field f : testClass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (!ConfigurableApplicationContext.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object val = f.get(null);
            if (val instanceof ConfigurableApplicationContext) {
                closeIfRunning((ConfigurableApplicationContext) val);
            }
        }

        // Also attempt to stop any static Testcontainers GenericContainer fields
        try {
            Class<?> genericContainerClass = Class.forName("org.testcontainers.containers.GenericContainer");
            for (Field f : testClass.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!genericContainerClass.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object val = f.get(null);
                if (val != null) {
                    try {
                        var stopM = val.getClass().getMethod("stop");
                        stopM.invoke(val);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (ClassNotFoundException ignored) {
        }

        // Attempt to close Testcontainers Docker client (noop if TC not on classpath)
        try {
            // Use reflection because older/newer TC versions may not expose a close() API
            Object inst = DockerClientFactory.class.getMethod("instance").invoke(null);
            try {
                var closeM = inst.getClass().getMethod("close");
                closeM.invoke(inst);
                } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }

        // Force-interrupt any lingering Tomcat threads that keep the JVM alive for tests
        try {
            Thread.getAllStackTraces().keySet().forEach(t -> {
                try {
                    String n = t.getName();
                        if (n != null && (n.startsWith("container-") || n.startsWith("Catalina-utility-"))) {
                        try {
                            t.interrupt();
                        } catch (Throwable ignored2) {
                        }
                        try {
                            // Wait up to 2s for Tomcat/Testcontainers threads to exit after interrupt
                            t.join(2000);
                        } catch (Throwable ignored3) {
                        }
                        if (t.isAlive()) {
                            try {
                                t.interrupt();
                                t.join(500);
                            } catch (Throwable ignored4) {
                            }
                        }
                    }
                } catch (Throwable ignored4) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void closeContextFromInstance(Object inst) {
        if (inst == null) return;
        Class<?> c = inst.getClass();
        for (Field f : c.getDeclaredFields()) {
            if (!ConfigurableApplicationContext.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            try {
                Object val = f.get(inst);
                if (val instanceof ConfigurableApplicationContext) {
                    closeIfRunning((ConfigurableApplicationContext) val);
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        // Also stop any Testcontainers fields on the instance
        try {
            Class<?> genericContainerClass = Class.forName("org.testcontainers.containers.GenericContainer");
            for (Field f : c.getDeclaredFields()) {
                if (!genericContainerClass.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                try {
                    Object val = f.get(inst);
                    if (val != null) {
                        try {
                            var stopM = val.getClass().getMethod("stop");
                            stopM.invoke(val);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
    }

    private void closeIfRunning(ConfigurableApplicationContext ctx) {
        try {
            // If it's a servlet web server context, stop the underlying web server first
            try {
                if (ctx instanceof ServletWebServerApplicationContext swc) {
                    var webServer = swc.getWebServer();
                    if (webServer != null) {
                        try {
                            System.out.println("[CloseSpringContextExtension] calling webServer.stop() on " + webServer.getClass().getName());
                            webServer.stop();
                            System.out.println("[CloseSpringContextExtension] webServer.stop() returned");
                        } catch (Throwable ignored) {
                            System.out.println("[CloseSpringContextExtension] webServer.stop() threw: " + ignored);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            // Extra: if Tomcat is used, try to stop the underlying Catalina Server instance too.
            try {
                if (ctx instanceof ServletWebServerApplicationContext swc) {
                    var webServer = swc.getWebServer();
                    if (webServer != null) {
                        try {
                            var getTomcat = webServer.getClass().getMethod("getTomcat");
                            Object tomcat = getTomcat.invoke(webServer);
                            System.out.println("[CloseSpringContextExtension] found tomcat instance: " + (tomcat==null?"null":tomcat.getClass().getName()));
                            if (tomcat != null) {
                                    try {
                                        var getServer = tomcat.getClass().getMethod("getServer");
                                        Object server = getServer.invoke(tomcat);
                                        System.out.println("[CloseSpringContextExtension] found server instance: " + (server==null?"null":server.getClass().getName()));
                                        if (server != null) {
                                            try {
                                                var stop = server.getClass().getMethod("stop");
                                                System.out.println("[CloseSpringContextExtension] calling server.stop()");
                                                stop.invoke(server);
                                                System.out.println("[CloseSpringContextExtension] server.stop() returned");
                                            } catch (Throwable ignored2) {
                                                System.out.println("[CloseSpringContextExtension] server.stop() not found or threw: " + ignored2);
                                            }
                                        }
                                    } catch (Throwable ignored2) {
                                        System.out.println("[CloseSpringContextExtension] getServer() not found on tomcat instance: " + ignored2);
                                    }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            if (ctx.isActive()) {
                ctx.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
