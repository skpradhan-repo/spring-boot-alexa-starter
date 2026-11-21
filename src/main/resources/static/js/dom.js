/**
 * Tiny element factory. Text is always assigned through {@code textContent} (never innerHTML), so anything a
 * user types or the backend returns is displayed literally and can never inject markup.
 */
export function h(tag, attributes = {}, ...children) {
    const element = document.createElement(tag);
    for (const [name, value] of Object.entries(attributes)) {
        if (value === false || value === null || value === undefined) {
            continue;
        }
        if (name === 'class') {
            element.className = value;
        } else if (name === 'text') {
            element.textContent = value;
        } else {
            element.setAttribute(name, value === true ? '' : String(value));
        }
    }
    element.append(...children.filter((child) => child !== null && child !== undefined && child !== false));
    return element;
}
