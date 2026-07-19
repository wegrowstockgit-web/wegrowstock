import { useCallback, useEffect, useRef, useState } from 'react';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';

export interface GeocodedAddress {
  formatted: string;
  street: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  latitude: number;
  longitude: number;
}

interface PlacesAddressInputProps {
  onResolved: (address: GeocodedAddress) => void;
  street: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

declare global {
  interface Window {
    google?: {
      maps: {
        places: {
          Autocomplete: new (
            input: HTMLInputElement,
            opts?: { types?: string[]; fields?: string[] },
          ) => {
            addListener: (event: string, handler: () => void) => void;
            getPlace: () => {
              formatted_address?: string;
              geometry?: { location?: { lat: () => number; lng: () => number } };
              address_components?: Array<{
                long_name: string;
                short_name: string;
                types: string[];
              }>;
            };
          };
        };
      };
    };
  }
}

/**
 * Google Places autocomplete when {@code VITE_GOOGLE_MAPS_API_KEY} is set;
 * otherwise deterministic client geocode for dock onboarding / e2e.
 */
export function PlacesAddressInput({
  onResolved,
  street,
  city,
  state,
  postalCode,
  country,
}: PlacesAddressInputProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [ready, setReady] = useState(false);
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined;

  useEffect(() => {
    if (!apiKey || !inputRef.current) {
      setReady(false);
      return;
    }
    let cancelled = false;
    const scriptId = 'invsys-google-maps-places';
    const boot = () => {
      if (cancelled || !inputRef.current || !window.google?.maps?.places) return;
      const autocomplete = new window.google.maps.places.Autocomplete(inputRef.current, {
        types: ['address'],
        fields: ['formatted_address', 'geometry', 'address_components'],
      });
      autocomplete.addListener('place_changed', () => {
        const place = autocomplete.getPlace();
        const lat = place.geometry?.location?.lat();
        const lng = place.geometry?.location?.lng();
        if (lat == null || lng == null) return;
        const comps = place.address_components ?? [];
        const find = (type: string) =>
          comps.find((c) => c.types.includes(type))?.long_name ?? '';
        onResolved({
          formatted: place.formatted_address ?? '',
          street: `${find('street_number')} ${find('route')}`.trim(),
          city: find('locality') || find('sublocality') || find('administrative_area_level_2'),
          state: find('administrative_area_level_1'),
          postalCode: find('postal_code'),
          country: comps.find((c) => c.types.includes('country'))?.short_name ?? 'US',
          latitude: lat,
          longitude: lng,
        });
      });
      setReady(true);
    };
    if (window.google?.maps?.places) {
      boot();
    } else if (!document.getElementById(scriptId)) {
      const script = document.createElement('script');
      script.id = scriptId;
      script.async = true;
      script.src = `https://maps.googleapis.com/maps/api/js?key=${apiKey}&libraries=places`;
      script.onload = boot;
      document.head.appendChild(script);
    }
    return () => {
      cancelled = true;
    };
  }, [apiKey, onResolved]);

  const mockGeocode = useCallback(() => {
    const composed = [street, city, state, postalCode, country].filter(Boolean).join(', ');
    const seed = composed || query || 'warehouse';
    let hash = 0;
    for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) | 0;
    const latitude = 30.2 + ((Math.abs(hash) % 1000) / 10000);
    const longitude = -97.8 - ((Math.abs(hash >> 8) % 1000) / 10000);
    onResolved({
      formatted: composed || seed,
      street: street || query,
      city: city || 'Austin',
      state: state || 'TX',
      postalCode: postalCode || '78701',
      country: country || 'US',
      latitude: Number(latitude.toFixed(7)),
      longitude: Number(longitude.toFixed(7)),
    });
  }, [street, city, state, postalCode, country, query, onResolved]);

  return (
    <div className="space-y-2" data-testid="places-address-input">
      <Input
        ref={inputRef}
        id="warehouse-places-search"
        name="placesSearch"
        label={apiKey ? 'Search address (Google Places)' : 'Address search'}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Start typing a facility address…"
        autoComplete="off"
      />
      {!apiKey && (
        <Button
          type="button"
          variant="secondary"
          size="sm"
          onClick={mockGeocode}
          data-testid="geocode-address"
        >
          Geocode address
        </Button>
      )}
      {apiKey && !ready && (
        <p className="text-xs text-text-muted">Loading Google Places…</p>
      )}
    </div>
  );
}
