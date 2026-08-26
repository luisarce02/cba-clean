import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
  effect,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import * as L from 'leaflet';

L.Icon.Default.imagePath = 'https://unpkg.com/leaflet@1.9.4/dist/images/';

@Component({
  selector: 'app-report-location-map',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './report-location-map.component.html',
  styleUrl: './report-location-map.component.scss',
})
export class ReportLocationMapComponent implements OnInit, OnDestroy, OnChanges {
  @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef<HTMLDivElement>;

  @Input() latitude: number | null = null;
  @Input() longitude: number | null = null;

  @Output() locationSelected = new EventEmitter<{ latitude: number; longitude: number }>();

  private map!: L.Map;
  private marker: L.Marker | null = null;

  private static readonly DEFAULT_CENTER: L.LatLngTuple = [-17.3935, -66.1570];
  private static readonly DEFAULT_ZOOM = 13;

  constructor() {
    effect(() => {
      const lat = this.latitude;
      const lng = this.longitude;
      if (this.map && lat !== null && lng !== null) {
        this.updateMarker(lat, lng);
        this.map.setView([lat, lng], this.map.getZoom());
      }
    });
  }

  ngOnInit(): void {
    this.map = L.map(this.mapContainer.nativeElement, {
      center: ReportLocationMapComponent.DEFAULT_CENTER,
      zoom: ReportLocationMapComponent.DEFAULT_ZOOM,
      attributionControl: true,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(this.map);

    this.map.on('click', (event: L.LeafletMouseEvent) => {
      const { lat, lng } = event.latlng;
      this.updateMarker(lat, lng);
      this.locationSelected.emit({ latitude: lat, longitude: lng });
    });

    if (this.latitude !== null && this.longitude !== null) {
      this.updateMarker(this.latitude, this.longitude);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.map) return;

    const latChanged = changes['latitude'];
    const lngChanged = changes['longitude'];

    const prevLat = latChanged?.previousValue;
    const prevLng = lngChanged?.previousValue;
    const currLat = latChanged?.currentValue;
    const currLng = lngChanged?.currentValue;

    const wasSet = prevLat != null && prevLng != null;
    const isNowCleared =
      (currLat == null || currLat === '') && (currLng == null || currLng === '');

    if (wasSet && isNowCleared) {
      this.resetMarker();
    }
  }

  ngOnDestroy(): void {
    if (this.map) {
      this.map.remove();
    }
  }

  useMyLocation(): void {
    if (!navigator.geolocation) return;

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude } = position.coords;
        this.updateMarker(latitude, longitude);
        this.map.setView([latitude, longitude], 15);
        this.locationSelected.emit({ latitude, longitude });
      },
      () => {
        // Permission denied or error — do nothing
      },
    );
  }

  resetMarker(): void {
    if (this.marker) {
      this.map.removeLayer(this.marker);
      this.marker = null;
    }
    this.map.setView(
      ReportLocationMapComponent.DEFAULT_CENTER,
      ReportLocationMapComponent.DEFAULT_ZOOM,
    );
  }

  private updateMarker(lat: number, lng: number): void {
    if (this.marker) {
      this.marker.setLatLng([lat, lng]);
    } else {
      this.marker = L.marker([lat, lng]).addTo(this.map);
    }
  }
}
