import React from 'react';
import { MapContainer, TileLayer, Marker, useMapEvents } from "react-leaflet";
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';

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

const MapClickHandler = ({ onLocationSelect }) => {
    useMapEvents({
        click(e) {
            onLocationSelect(e.latlng);
        },
    });
    return null;
};

const MapPicker = ({ position, onLocationSelect }) => {
    return (
        <div style={{
            height: '400px',
            marginBottom: '1rem',
            borderRadius: '8px',
            overflow: 'hidden',
            border: '2px solid #e0e0e0'
        }}>
            <MapContainer
                center={position || [55.9533, -3.1883]} // Edinburgh default
                zoom={13}
                style={{ height: '100%', width: '100%' }}
            >
                <TileLayer
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                />
                {position && <Marker position={position} />}
                <MapClickHandler onLocationSelect={onLocationSelect} />
            </MapContainer>
        </div>
    );
};

export default MapPicker;