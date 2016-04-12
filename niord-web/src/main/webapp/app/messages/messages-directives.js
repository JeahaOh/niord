/**
 * Message list directives.
 */
angular.module('niord.messages')

    /**
     * Replaces the content of the element with the area description
     */
    .directive('renderAreas', ['LangService', function (LangService) {
        return {
            restrict: 'A',
            scope: {
                renderAreas: "=",
                areaDivider: "@"
            },
            link: function(scope, element, attrs) {
                var divider = (attrs.areaDivider) ? attrs.areaDivider : " - ";

                scope.updateAreas = function(areas) {
                    var result = '';
                    if (areas && areas.length > 0) {
                        for (var area = areas[0]; area; area = area.parent) {
                            var desc = LangService.desc(area);
                            var areaName = (desc) ? desc.name : '';
                            result = areaName + ((result.length > 0 && areaName.length > 0) ? divider : '') + result;
                        }
                    }
                    element.html(result);
                };

                scope.$watchCollection("renderAreas", scope.updateAreas);
            }
        };
    }])


    /**
     * The map-message-list-layer directive supports drawing a list of messages on a map layer
     */
    .directive('mapMessageListLayer', ['$rootScope', 'MapService', function ($rootScope, MapService) {
        return {
            restrict: 'E',
            replace: false,
            template: '<div id="msg-info"/>',
            require: '^olMap',
            scope: {
                name: '@',
                visible: '=',
                layerSwitcher: '=',
                messageList: '=',
                showOutline: '@',
                fitExtent: '@',
                maxZoom: '@'
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;
                var maxZoom = scope.maxZoom ? parseInt(scope.maxZoom) : 10;


                olScope.getMap().then(function(map) {

                    // Clean up when the layer is destroyed
                    scope.$on('$destroy', function() {
                        if (angular.isDefined(olLayer)) {
                            map.removeLayer(olLayer);
                        }
                    });

                    /***************************/
                    /** Construct Layer       **/
                    /***************************/

                    var styleImage = (scope.showOutline == 'true')
                        ?  new ol.style.Circle({
                                radius: 4,
                                stroke: new ol.style.Stroke({
                                    color: '#8B008B',
                                    width: 1
                                }),
                                fill: new ol.style.Fill({
                                    color: 'rgba(255, 0, 255, 0.6)'
                                })
                            })
                        : new ol.style.Icon({
                                anchor: [0.5, 0.5],
                                scale: 0.3,
                                src: '/img/msi.png'
                            });

                    var style = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(255, 0, 255, 0.2)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: '#8B008B',
                            width: 1
                        }),
                        image: styleImage
                    });

                    var bufferedStyle = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(100, 50, 100, 0.2)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: 'rgba(100, 50, 100, 0.8)',
                            width: 1
                        })
                    });

                    // Construct the layer
                    var features = new ol.Collection();
                    olLayer = new ol.layer.Vector({
                        source: new ol.source.Vector({
                            features: features,
                            wrapX: false
                        }),
                        style: function(feature) {
                            var featureStyle;
                            if (feature.get('parentFeatureId')) {
                                featureStyle = bufferedStyle;
                            } else {
                                featureStyle = style;
                            }

                            return [ featureStyle ];
                        }
                    });
                    olLayer = MapService.initLayer(olLayer, scope.name, scope.visible, scope.layerSwitcher);
                    map.addLayer(olLayer);


                    /***************************/
                    /** Message List Handling **/
                    /***************************/

                    scope.updateLayerFromMessageList = function () {
                        olLayer.getSource().clear();
                        if (scope.messageList && scope.messageList.length > 0) {
                            angular.forEach(scope.messageList, function (message) {
                                if (message.geometry && message.geometry.features.length > 0) {

                                    angular.forEach(message.geometry.features, function (gjFeature) {
                                        var olFeature = MapService.gjToOlFeature(gjFeature);
                                        if (scope.showOutline == 'true') {
                                            var point = MapService.getGeometryCenter(olFeature.getGeometry());
                                            if (point) {
                                                olFeature.setGeometry(new ol.geom.Point(point));
                                            }
                                        }
                                        olLayer.getSource().addFeature(olFeature);
                                    });
                                }
                            });
                            if (scope.fitExtent == 'true') {
                                map.getView().fit(olLayer.getSource().getExtent(), map.getSize(), {
                                    padding: [5, 5, 5, 5],
                                    maxZoom: maxZoom
                                });
                            }
                        }
                    };

                    scope.$watchCollection("messageList", scope.updateLayerFromMessageList, true);


                    /***************************/
                    /** Tooltips              **/
                    /***************************/


                    // Returns the list of messages for the given pixel
                    scope.getFeatureForPixel = function (pixel) {
                        var msgs = [];
                        map.forEachFeatureAtPixel(pixel, function(feature, layer) {
                            // test
                            if (layer  == olLayer) {
                                msgs.push(feature);
                            }
                        });
                        return msgs;
                    };


                    // Prepare the tooltip
                    var info = $('#msg-info');
                    info.tooltip({
                        animation: false,
                        trigger: 'manual',
                        html: true,
                        placement: 'bottom'
                    });


                    // Show tooltip info
                    var updateMsgTooltip = function(pixel) {
                        var features = scope.getFeatureForPixel(pixel);
                        var langKey = "name#" + $rootScope.language;

                        // Build the html to display in the tooltip
                        var html = '';
                        angular.forEach(features, function (feature) {
                            var name = feature.get(langKey);
                            if (name) {
                                html += '<div><small>' + name + '</small></div>';
                            }
                        });

                        if (html.length > 0) {

                            // Update the tooltip
                            info.css({
                                left: pixel[0] + 'px',
                                top: (pixel[1] + 15) + 'px'
                            });
                            info.tooltip('hide')
                                .attr('data-original-title', html)
                                .tooltip('fixTitle')
                                .tooltip('show');

                        } else {
                            info.tooltip('hide');
                        }
                    };


                    // Update the tooltip whenever the mouse is moved
                    map.on('pointermove', function(evt) {
                        if (evt.dragging) {
                            info.tooltip('hide');
                            return;
                        }
                        updateMsgTooltip(map.getEventPixel(evt.originalEvent));
                    });

                });

            }
        };
    }])

    /**
     * The map-message-details directive supports drawing a single message on a map layer
     */
    .directive('mapMessageDetailsLayer', ['$rootScope', 'MapService', function ($rootScope, MapService) {
        return {
            restrict: 'E',
            replace: false,
            template: '<div id="msg-info"/>',
            require: '^olMap',
            scope: {
                name: '@',
                visible: '=',
                layerSwitcher: '=',
                message: '=',
                showOutline: '@',
                fitExtent: '@',
                maxZoom: '@'
            },
            link: function(scope, element, attrs, ctrl) {
                var olScope = ctrl.getOpenlayersScope();
                var olLayer;
                var maxZoom = scope.maxZoom ? parseInt(scope.maxZoom) : 12;


                olScope.getMap().then(function(map) {

                    // Clean up when the layer is destroyed
                    scope.$on('$destroy', function() {
                        if (angular.isDefined(olLayer)) {
                            map.removeLayer(olLayer);
                        }
                    });

                    /***************************/
                    /** Construct Layer       **/
                    /***************************/

                    var styleImage = (scope.showOutline == 'true')
                        ?  new ol.style.Circle({
                        radius: 4,
                        stroke: new ol.style.Stroke({
                            color: '#8B008B',
                            width: 1
                        }),
                        fill: new ol.style.Fill({
                            color: 'rgba(255, 0, 255, 0.6)'
                        })
                    })
                        : new ol.style.Icon({
                        anchor: [0.5, 0.5],
                        scale: 0.3,
                        src: '/img/msi.png'
                    });

                    var style = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(255, 0, 255, 0.2)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: '#8B008B',
                            width: 1
                        }),
                        image: styleImage
                    });

                    var bufferedStyle = new ol.style.Style({
                        fill: new ol.style.Fill({
                            color: 'rgba(100, 50, 100, 0.2)'
                        }),
                        stroke: new ol.style.Stroke({
                            color: 'rgba(100, 50, 100, 0.8)',
                            width: 1
                        })
                    });

                    // Construct the layer
                    var features = new ol.Collection();
                    olLayer = new ol.layer.Vector({
                        source: new ol.source.Vector({
                            features: features,
                            wrapX: false
                        }),
                        style: function(feature) {
                            var featureStyle;
                            if (feature.get('parentFeatureId')) {
                                featureStyle = bufferedStyle;
                            } else {
                                featureStyle = style;
                            }

                            return [ featureStyle ];
                        }
                    });
                    olLayer = MapService.initLayer(olLayer, scope.name, scope.visible, scope.layerSwitcher);
                    map.addLayer(olLayer);


                    /***************************/
                    /** Message List Handling **/
                    /***************************/

                    scope.updateLayerFromMessage = function () {
                        olLayer.getSource().clear();
                        if (scope.message) {
                            // TODO: For now messages are feature collections
                            var featureCollection = scope.message;
                            if (featureCollection.features.length > 0) {

                                angular.forEach(featureCollection.features, function (gjFeature) {
                                    var olFeature = MapService.gjToOlFeature(gjFeature);
                                    if (scope.showOutline == 'true') {
                                        var point = MapService.getGeometryCenter(olFeature.getGeometry());
                                        if (point) {
                                            olFeature.setGeometry(new ol.geom.Point(point));
                                        }
                                    }
                                    olLayer.getSource().addFeature(olFeature);
                                });
                            }

                            if (scope.fitExtent == 'true' && olLayer.getSource().getFeatures().length > 0) {
                                map.getView().fit(olLayer.getSource().getExtent(), map.getSize(), {
                                    padding: [20, 20, 20, 20],
                                    maxZoom: maxZoom
                                });
                            }
                        }
                    };

                    scope.$watch("message", scope.updateLayerFromMessage, true);


                    /***************************/
                    /** Tooltips              **/
                    /***************************/


                    // Returns the list of messages for the given pixel
                    scope.getFeatureForPixel = function (pixel) {
                        var msgs = [];
                        map.forEachFeatureAtPixel(pixel, function(feature, layer) {
                            // test
                            if (layer  == olLayer) {
                                msgs.push(feature);
                            }
                        });
                        return msgs;
                    };


                    // Prepare the tooltip
                    var info = $('#msg-info');
                    info.tooltip({
                        animation: false,
                        trigger: 'manual',
                        html: true,
                        placement: 'bottom'
                    });


                    // Show tooltip info
                    var updateMsgTooltip = function(pixel) {
                        var features = scope.getFeatureForPixel(pixel);
                        var langKey = "name#" + $rootScope.language;

                        // Build the html to display in the tooltip
                        var html = '';
                        angular.forEach(features, function (feature) {
                            var name = feature.get(langKey);
                            if (name) {
                                html += '<div><small>' + name + '</small></div>';
                            }
                        });

                        if (html.length > 0) {

                            // Update the tooltip
                            info.css({
                                left: pixel[0] + 'px',
                                top: (pixel[1] + 15) + 'px'
                            });
                            info.tooltip('hide')
                                .attr('data-original-title', html)
                                .tooltip('fixTitle')
                                .tooltip('show');

                        } else {
                            info.tooltip('hide');
                        }
                    };


                    // Update the tooltip whenever the mouse is moved
                    map.on('pointermove', function(evt) {
                        if (evt.dragging) {
                            info.tooltip('hide');
                            return;
                        }
                        updateMsgTooltip(map.getEventPixel(evt.originalEvent));
                    });

                });

            }
        };
    }])


    /****************************************************************
     * Binds a click event that will open the message details dialog
     ****************************************************************/
    .directive('messageDetailsLink', ['$rootScope',
        function ($rootScope) {
            'use strict';

            return {
                restrict: 'A',
                scope: {
                    messageDetailsLink: "=",
                    messageList: "=",
                    disabled: "=?"
                },
                link: function(scope, element) {

                    if (!scope.disabled) {
                        element.addClass('clickable');
                        element.bind('click', function() {
                            $rootScope.$broadcast('messageDetails', {
                                messageId: scope.messageDetailsLink,
                                messageList: scope.messageList
                            });
                        });
                    }
                }
            };
        }])


    /********************************
     * Renders the message details
     ********************************/
    .directive('renderMessageDetails', [ '$rootScope', 'MapService', function ($rootScope, MapService) {
        'use strict';

        return {
            restrict: 'A',
            templateUrl: '/app/messages/render-message-details.html',
            replace: false,
            scope: {
                msg: "=",
                messageList: "=",
                format: "@"
            },
            link: function(scope) {
                scope.language = $rootScope.language;
                scope.format = scope.format || 'list';

                scope.coordinates = [];

                scope.serializeCoordinates = function () {
                    // Compute on-demand
                    if (scope.coordinates.length == 0 && scope.msg.geometry) {
                        MapService.serializeCoordinates(scope.msg.geometry, scope.coordinates, {}, 0, true);
                    }
                    return scope.coordinates;
                }
            }
        };
    }]);
