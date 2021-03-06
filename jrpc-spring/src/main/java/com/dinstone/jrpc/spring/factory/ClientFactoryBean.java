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

package com.dinstone.jrpc.spring.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AbstractFactoryBean;

import com.dinstone.jrpc.api.Client;

public class ClientFactoryBean extends AbstractFactoryBean<Client> {

    private static final Logger LOG = LoggerFactory.getLogger(ClientFactoryBean.class);

    private String id;

    private String name;

    // ================================================
    // Transport config
    // ================================================
    private ConfigBean transportBean;

    // ================================================
    // Registry config
    // ================================================
    private ConfigBean registryBean;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConfigBean getTransportBean() {
        return transportBean;
    }

    public void setTransportBean(ConfigBean transportBean) {
        this.transportBean = transportBean;
    }

    public ConfigBean getRegistryBean() {
        return registryBean;
    }

    public void setRegistryBean(ConfigBean registryBean) {
        this.registryBean = registryBean;
    }

    @Override
    protected Client createInstance() throws Exception {
        LOG.info("create jrpc client {}@{}", id, name);

        Client client = null;
        String address = transportBean.getAddress();
        if (address != null && !address.isEmpty()) {
            client = new Client(address);
        } else {
            client = new Client();
        }
        client.getTransportConfig().setSchema(transportBean.getSchema());
        client.getTransportConfig().setProperties(transportBean.getProperties());

        if (registryBean.getSchema() != null && !registryBean.getSchema().isEmpty()) {
            client.getRegistryConfig().setSchema(registryBean.getSchema());
            client.getRegistryConfig().setProperties(registryBean.getProperties());
        }

        client.getEndpointConfig().setEndpointId(id);
        client.getEndpointConfig().setEndpointName(name);

        return client;
    }

    @Override
    protected void destroyInstance(Client instance) throws Exception {
        instance.destroy();
    }

    @Override
    public Class<?> getObjectType() {
        return Client.class;
    }
}
