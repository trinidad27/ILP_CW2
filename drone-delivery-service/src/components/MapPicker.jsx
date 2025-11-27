import React, { useState } from 'react';
import { MapContainer, TileLayer, Marker, useMapEvents} from "react-leaflet";
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';

delete L.icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: require('leaflet/dist/images/marker-icon-2x.png'),
    iconUrl: require('leaflet/dist/images/marker-icon.png'),
    shadowUrl: require('leaflet/dist/images/marker-shadow.png'),
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
        <div style={{ height: '400px', marginBottom: '1rem', borderRadius: '8px', overflow: 'hidden' }}>
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