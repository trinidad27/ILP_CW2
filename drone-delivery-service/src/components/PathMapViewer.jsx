import React, { useEffect, useRef } from 'react';
import { MapContainer, TileLayer, GeoJSON, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import './PathMapViewer.css';

// Fix Leaflet default marker icons
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: markerIcon2x,
    iconUrl: markerIcon,
    shadowUrl: markerShadow,
});

// Component to fit map bounds to path
const FitBounds = ({ geojson }) => {
    const map = useMap();

    useEffect(() => {
        if (geojson && geojson.type === 'FeatureCollection' && geojson.features.length > 0) {
            const geoJsonLayer = L.geoJSON(geojson);
            const bounds = geoJsonLayer.getBounds();

            if (bounds.isValid()) {
                map.fitBounds(bounds, { padding: [50, 50] });
            }
        }
    }, [geojson, map]);

    return null;
};

const PathMapViewer = ({ pathData }) => {
    const mapRef = useRef(null);

    if (!pathData) {
        return null;
    }

    // Extract GeoJSON from pathData
    // Your backend returns: { totalCost, totalMoves, dronePaths: [...] }
    // Each dronePath has: { droneId, geojson, moves, cost }

    const renderPathInfo = () => {
        return (
            <div className="path-info">
                <h3>Delivery Path Calculated</h3>
                <div className="path-stats">
                    <div className="stat">
                        <span className="stat-label">Total Moves:</span>
                        <span className="stat-value">{pathData.totalMoves || 0}</span>
                    </div>
                    <div className="stat">
                        <span className="stat-label">Total Cost:</span>
                        <span className="stat-value">${(pathData.totalCost || 0).toFixed(2)}</span>
                    </div>
                    <div className="stat">
                        <span className="stat-label">Drones Used:</span>
                        <span className="stat-value">{pathData.dronePaths?.length || 0}</span>
                    </div>
                </div>

                {pathData.dronePaths && pathData.dronePaths.length > 0 && (
                    <div className="drone-paths-list">
                        <h4>Drone Assignments:</h4>
                        {pathData.dronePaths.map((dronePath, index) => (
                            <div key={index} className="drone-path-item">
                                <strong>Drone {dronePath.droneId}</strong>: {dronePath.moves} moves, ${dronePath.cost?.toFixed(2)} cost
                            </div>
                        ))}
                    </div>
                )}
            </div>
        );
    };

    // Get the first drone's GeoJSON for map display
    const geojson = pathData.dronePaths && pathData.dronePaths.length > 0
        ? pathData.dronePaths[0].geojson
        : null;

    // Style for GeoJSON lines
    const pathStyle = (feature) => {
        return {
            color: '#3388ff',
            weight: 4,
            opacity: 0.8,
            dashArray: feature.properties?.isReturn ? '10, 10' : null
        };
    };

    // Handle each GeoJSON feature
    const onEachFeature = (feature, layer) => {
        if (feature.properties) {
            const popupContent = `
        <div>
          <strong>${feature.properties.name || 'Path'}</strong><br/>
          ${feature.properties.description || ''}
        </div>
      `;
            layer.bindPopup(popupContent);
        }
    };

    return (
        <div className="path-map-viewer">
            {renderPathInfo()}

            {geojson ? (
                <div className="map-container">
                    <MapContainer
                        ref={mapRef}
                        center={[55.9533, -3.1883]} // Edinburgh default
                        zoom={13}
                        style={{ height: '500px', width: '100%' }}
                    >
                        <TileLayer
                            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                        />

                        <GeoJSON
                            data={geojson}
                            style={pathStyle}
                            onEachFeature={onEachFeature}
                        />

                        <FitBounds geojson={geojson} />
                    </MapContainer>
                </div>
            ) : (
                <div className="no-path-message">
                    <p>No path data available to display on map.</p>
                    <small>The delivery path will appear here after calculation.</small>
                </div>
            )}
        </div>
    );
};

export default PathMapViewer;