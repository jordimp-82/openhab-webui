/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.ui.homebuilder.internal;

import org.openhab.core.ui.tiles.Tile;
import org.osgi.service.component.annotations.Component;

/**
 * The tile for Home Builder
 *
 * @author Kuba Wolanin - Initial contribution
 * @author Yannick Schaus - remove dependency to dashboard
 *
 */
@Component
public class HomeBuilderTile implements Tile {

    @Override
    public String getName() {
        return "Home Builder";
    }

    @Override
    public String getUrl() {
        return "../homebuilder/index.html";
    }

    @Override
    public String getOverlay() {
        return null;
    }

    @Override
    public String getImageUrl() {
        return "../homebuilder/tile.png";
    }
}
