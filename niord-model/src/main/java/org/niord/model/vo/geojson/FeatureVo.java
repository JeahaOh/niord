/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.model.vo.geojson;


import io.swagger.annotations.ApiModel;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * GeoJSON Feature, as defined in the specification:
 * http://geojson.org/geojson-spec.html#feature-objects
 */
@ApiModel(value = "Feature", description = "GeoJson Feature type")
@XmlRootElement(name = "feature")
public class FeatureVo extends GeoJsonVo {

    private Object id;
    private GeometryVo geometry;
    private Properties properties = new Properties();

    /** {@inheritDoc} */
    @Override
    public void visitCoordinates(Consumer<double[]> handler) {
        if (geometry != null) {
            geometry.visitCoordinates(handler);
        }
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    @XmlElementRef
    public GeometryVo getGeometry() {
        return geometry;
    }

    public void setGeometry(GeometryVo geometry) {
        this.geometry = geometry;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }
}

