/**
 * Offline / fault-injection provider.
 *
 * Two jobs:
 *   1. Let the whole location flow be designed with no network at all (§26).
 *   2. Make the failure states reachable on demand, so empty results, timeouts
 *      and rate limiting get designed rather than discovered in the field (§11).
 *
 * The dataset spans several countries and categories on purpose — it is the
 * cheapest standing proof that search is generic (§4).
 */

import type { SearchResultLocation } from '../../domain/types'
import { SearchError, type LocationSearchProvider } from './LocationSearchProvider'

export type Fault = 'none' | 'network' | 'timeout' | 'rate-limited' | 'provider' | 'malformed' | 'empty'

interface Row {
  name: string
  address: string
  lat: number
  lon: number
  category: string
}

const PLACES: Row[] = [
  // Rajasthan
  { name: 'City Palace', address: 'City Palace Complex, Udaipur, Rajasthan, India', lat: 24.5764, lon: 73.6835, category: 'attraction' },
  { name: 'Lake Pichola', address: 'Udaipur, Rajasthan, India', lat: 24.5716, lon: 73.6788, category: 'lake' },
  { name: 'Jagdish Temple', address: 'Jagdish Chowk, Udaipur, Rajasthan, India', lat: 24.5793, lon: 73.6836, category: 'temple' },
  { name: 'Ambrai Ghat', address: 'Outside Chandpole, Udaipur, Rajasthan, India', lat: 24.5768, lon: 73.6789, category: 'viewpoint' },
  { name: 'Monsoon Palace', address: 'Sajjangarh, Udaipur, Rajasthan, India', lat: 24.5936, lon: 73.6482, category: 'attraction' },
  { name: 'Saheliyon Ki Bari', address: 'Saheli Marg, Udaipur, Rajasthan, India', lat: 24.5977, lon: 73.69, category: 'garden' },
  { name: 'Udaipur City Railway Station', address: 'Station Road, Udaipur, Rajasthan, India', lat: 24.5726, lon: 73.6919, category: 'railway station' },
  { name: 'Maharana Pratap Airport', address: 'Dabok, Udaipur, Rajasthan, India', lat: 24.6177, lon: 73.8961, category: 'airport' },
  { name: 'Taj Mahal', address: 'Dharmapuri, Agra, Uttar Pradesh, India', lat: 27.1751, lon: 78.0421, category: 'attraction' },
  { name: 'Agra Cantt Railway Station', address: 'Idgah Colony, Agra, Uttar Pradesh, India', lat: 27.1573, lon: 77.9986, category: 'railway station' },
  { name: 'Amber Fort', address: 'Devisinghpura, Amer, Jaipur, Rajasthan, India', lat: 26.9855, lon: 75.8513, category: 'fort' },
  { name: 'Hawa Mahal', address: 'Hawa Mahal Road, Badi Choupad, Jaipur, Rajasthan, India', lat: 26.9239, lon: 75.8267, category: 'attraction' },
  // Elsewhere in India
  { name: 'Nagpur Junction', address: 'Santra Market, Nagpur, Maharashtra, India', lat: 21.1533, lon: 79.0938, category: 'railway station' },
  { name: 'Key Monastery', address: 'Kyi, Lahaul and Spiti, Himachal Pradesh, India', lat: 32.2977, lon: 78.0119, category: 'monastery' },
  { name: 'Fort Kochi Beach', address: 'Fort Nagar, Kochi, Kerala, India', lat: 9.9647, lon: 76.2422, category: 'beach' },
  { name: 'Virupaksha Temple', address: 'Hampi, Ballari, Karnataka, India', lat: 15.3350, lon: 76.4600, category: 'temple' },
  { name: 'Blue Tokai Coffee Roasters', address: 'Church Street, Bengaluru, Karnataka, India', lat: 12.9752, lon: 77.6035, category: 'cafe' },
  { name: 'Indian Museum', address: 'Park Street, Kolkata, West Bengal, India', lat: 22.5580, lon: 88.3510, category: 'museum' },
  // Deliberately far afield — the app is not an India app either.
  { name: 'Fushimi Inari Taisha', address: 'Fukakusa Yabunouchicho, Fushimi Ward, Kyoto, Japan', lat: 34.9671, lon: 135.7727, category: 'shrine' },
  { name: 'Kyoto Station', address: 'Higashishiokoji, Shimogyo Ward, Kyoto, Japan', lat: 34.9858, lon: 135.7588, category: 'railway station' },
  { name: 'Hotel Borg', address: 'Pósthússtræti 11, Reykjavík, Iceland', lat: 64.1475, lon: -21.9366, category: 'hotel' },
  { name: 'Café de Flore', address: '172 Boulevard Saint-Germain, Paris, France', lat: 48.8542, lon: 2.3327, category: 'cafe' },
  { name: 'Ponte Vecchio', address: 'Ponte Vecchio, Florence, Tuscany, Italy', lat: 43.7680, lon: 11.2531, category: 'landmark' },
  { name: 'Tsukiji Outer Market', address: 'Tsukiji, Chuo City, Tokyo, Japan', lat: 35.6654, lon: 139.7707, category: 'market' },
]

const asResult = (row: Row): SearchResultLocation => ({
  name: row.name,
  formattedAddress: row.address,
  latitude: row.lat,
  longitude: row.lon,
  category: row.category,
  providerPlaceId: `mock:${row.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`,
  providerName: 'Offline sample',
})

const sleep = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve, reject) => {
    const t = setTimeout(resolve, ms)
    signal.addEventListener('abort', () => {
      clearTimeout(t)
      reject(new DOMException('Aborted', 'AbortError'))
    })
  })

export class MockProvider implements LocationSearchProvider {
  readonly name = 'Offline sample'

  constructor(private readonly fault: () => Fault = () => 'none') {}

  async search(query: string, signal: AbortSignal): Promise<SearchResultLocation[]> {
    const fault = this.fault()

    // A little latency, so loading states are visible and worth designing.
    await sleep(fault === 'timeout' ? 12_000 : 260, signal)

    switch (fault) {
      case 'network':
        throw new SearchError('network', 'Simulated offline')
      case 'rate-limited':
        throw new SearchError('rate-limited', 'Simulated rate limit')
      case 'provider':
        throw new SearchError('provider', 'Simulated provider outage')
      case 'malformed':
        throw new SearchError('malformed', 'Simulated malformed payload')
      case 'empty':
        return []
      default:
        break
    }

    const needle = query.trim().toLowerCase()
    const tokens = needle.split(/\s+/).filter(Boolean)

    return PLACES.filter((row) => {
      const haystack = `${row.name} ${row.address} ${row.category}`.toLowerCase()
      return tokens.every((t) => haystack.includes(t))
    })
      .slice(0, 12)
      .map(asResult)
  }
}
