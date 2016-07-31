package com.ai.cloud.skywalking.agent;

import com.ai.cloud.skywalking.agent.junction.SkyWalkingEnhanceMatcher;
import com.ai.cloud.skywalking.conf.AuthDesc;
import com.ai.cloud.skywalking.conf.Config;
import com.ai.cloud.skywalking.logging.LogManager;
import com.ai.cloud.skywalking.logging.Logger;
import com.ai.cloud.skywalking.plugin.*;
import com.ai.cloud.skywalking.plugin.boot.IBootPluginDefine;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.List;

public class SkywalkingAgent {

    private static       Logger               logger               = LogManager.getLogger(SkywalkingAgent.class);
    private static final PluginDefineCategory pluginDefineCategory = PluginDefineCategory.category(new PluginBootstrap().loadPlugins());

    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        initConfig();
        if (AuthDesc.isAuth()) {
            startBootPluginDefines(pluginDefineCategory.getBootPluginsDefines());

            new AgentBuilder.Default().type(exclusivePackageClass()).transform(new AgentBuilder.Transformer() {
                public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader) {
                    AbstractClassEnhancePluginDefine pluginDefine = pluginDefineCategory.findPluginDefine(typeDescription.getTypeName());
                    try {
                        return pluginDefine.define(builder);
                    } catch (Throwable e) {
                        logger.error("Failed to enhance plugin " + pluginDefine.getClass().getName(), e);
                        return builder;
                    }
                }
            }).installOn(instrumentation);

        }
    }

    private static ElementMatcher.Junction<NamedElement> exclusivePackageClass() {
        return enhanceClassMatcher();
    }

    private static <T extends NamedElement> ElementMatcher.Junction<T> enhanceClassMatcher() {
        return new SkyWalkingEnhanceMatcher<T>(pluginDefineCategory);
    }


    public static void startBootPluginDefines(List<IBootPluginDefine> IBootPluginDefines) throws PluginException {
        for (IBootPluginDefine bootPluginDefine : IBootPluginDefines) {
            bootPluginDefine.boot();
        }
    }


    private static String generateLocationPath() {
        return SkywalkingAgent.class.getName().replaceAll("\\.", "/") + ".class";
    }


    private static void initConfig() {
        Config.SkyWalking.IS_PREMAIN_MODE = true;
        Config.SkyWalking.AGENT_BASE_PATH = initAgentBasePath();
    }

    private static String initAgentBasePath() {
        try {
            String urlString = SkywalkingAgent.class.getClassLoader().getSystemClassLoader().getResource(generateLocationPath()).toString();
            urlString = urlString.substring(urlString.indexOf("file:"), urlString.indexOf('!'));
            return new File(new URL(urlString).getFile()).getParentFile().getAbsolutePath();
        } catch (Exception e) {
            logger.error("Failed to init config .", e);
            return "";
        }
    }
}
