/*
 * Copyright (C) 2014~2016 dinstone<dinstone@163.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dinstone.jrpc.srd.zookeeper;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceProvider;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

import com.dinstone.jrpc.srd.ServiceAttribute;
import com.dinstone.jrpc.srd.ServiceDescription;

public class ZookeeperServiceDiscovery implements com.dinstone.jrpc.srd.ServiceDiscovery {

    private CuratorFramework zkClient;

    private ServiceDiscovery<ServiceAttribute> serviceDiscovery;

    private Map<String, ServiceProvider<ServiceAttribute>> providers = new HashMap<String, ServiceProvider<ServiceAttribute>>();

    public ZookeeperServiceDiscovery(RegistryDiscoveryConfig discoveryConfig) {
        String zkNodes = discoveryConfig.getZookeeperNodes();
        if (zkNodes == null || zkNodes.length() == 0) {
            throw new IllegalArgumentException("zookeeper.node.list is empty");
        }

        String basePath = discoveryConfig.getBasePath();
        if (basePath == null || basePath.length() == 0) {
            throw new IllegalArgumentException("basePath is empty");
        }

        zkClient = CuratorFrameworkFactory.newClient(zkNodes,
            new ExponentialBackoffRetry(discoveryConfig.getBaseSleepTime(), discoveryConfig.getMaxRetries()));
        zkClient.start();

        try {
            serviceDiscovery = ServiceDiscoveryBuilder.builder(ServiceAttribute.class).client(zkClient)
                .basePath(basePath).serializer(new JsonInstanceSerializer<ServiceAttribute>(ServiceAttribute.class))
                .build();
            serviceDiscovery.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ServiceDescription> discovery(String serviceName, String group) throws Exception {
        List<ServiceDescription> serviceDescriptions = new LinkedList<ServiceDescription>();
        synchronized (providers) {
            String key = serviceName + "-" + group;
            ServiceProvider<ServiceAttribute> serviceProvider = providers.get(key);
            if (serviceProvider != null) {
                Collection<ServiceInstance<ServiceAttribute>> sic = serviceProvider.getAllInstances();
                for (ServiceInstance<ServiceAttribute> serviceInstance : sic) {
                    ServiceDescription description = new ServiceDescription();
                    description.setId(serviceInstance.getId());
                    String[] nameGroup = serviceInstance.getName().split("-", 2);
                    if (nameGroup.length > 1) {
                        description.setName(nameGroup[0]);
                        description.setGroup(nameGroup[1]);
                    } else {
                        description.setName(serviceInstance.getName());
                        description.setGroup("");
                    }
                    description.setHost(serviceInstance.getAddress());
                    description.setPort(serviceInstance.getPort());
                    description.setRegistryTime(serviceInstance.getRegistrationTimeUTC());
                    description.setServiceAttribute(serviceInstance.getPayload());

                    serviceDescriptions.add(description);
                }
            }
        }
        return serviceDescriptions;

    }

    @Override
    public void listen(String serviceName, String group) throws Exception {
        if (serviceName == null || serviceName.length() == 0) {
            throw new IllegalArgumentException("serviceName is empty");
        }
        if (group == null) {
            throw new IllegalArgumentException("group is null");
        }

        String key = serviceName + "-" + group;
        synchronized (providers) {
            if (!providers.containsKey(key)) {
                ServiceProvider<ServiceAttribute> serviceProvider = serviceDiscovery.serviceProviderBuilder()
                    .serviceName(key).build();
                serviceProvider.start();
                providers.put(key, serviceProvider);
            }
        }
    }

    @Override
    public void cancel(String serviceName, String group) {
        String key = serviceName + "-" + group;
        synchronized (providers) {
            if (providers.containsKey(key)) {
                ServiceProvider<ServiceAttribute> provider = providers.remove(key);
                try {
                    provider.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @Override
    public void destroy() {
        synchronized (providers) {
            for (ServiceProvider<ServiceAttribute> provider : providers.values()) {
                try {
                    provider.close();
                } catch (IOException e) {
                }
            }
            providers.clear();
        }

        try {
            serviceDiscovery.close();
        } catch (IOException e) {
        }

        zkClient.close();
    }

}