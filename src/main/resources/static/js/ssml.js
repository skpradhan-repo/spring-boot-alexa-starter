const ENTITIES = { '&lt;': '<', '&gt;': '>', '&quot;': '"', '&apos;': "'", '&amp;': '&' };

/**
 * Converts an SSML document or fragment to the plain text a user would read or a browser would speak.
 * Tags are removed first and entities decoded afterwards, so escaped markup in user text stays visible as text.
 */
export function ssmlToText(ssml) {
    if (typeof ssml !== 'string') {
        return '';
    }
    return ssml
        .replace(/<[^>]*>/g, '')
        .replace(/&(?:lt|gt|quot|apos|amp);/g, (entity) => ENTITIES[entity])
        .replace(/\s+/g, ' ')
        .trim();
}
