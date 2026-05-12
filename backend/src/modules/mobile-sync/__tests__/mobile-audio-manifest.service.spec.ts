import { buildMessageLibrary } from '../mobile-audio-manifest.service';

describe('mobile-audio-manifest.service', () => {
  it('buildMessageLibrary returns empty list for no exercises', () => {
    expect(buildMessageLibrary([])).toEqual([]);
  });
});
