'use strict';
const buscar = document.querySelector('#buscar-rancho');
const selector = document.querySelector('#selector-rancho');
if (buscar && selector) {
  const opciones = Array.from(selector.options).map(o => ({value:o.value, text:o.textContent}));
  const normalizar = s => s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase('es');
  buscar.addEventListener('input', () => {
    const anterior = selector.value;
    const filtradas = opciones.filter(o => normalizar(o.text).includes(normalizar(buscar.value.trim())));
    selector.replaceChildren(...filtradas.map(o => new Option(o.text, o.value)));
    selector.value = filtradas.some(o => o.value === anterior) ? anterior : (filtradas[0]?.value || '');
    document.querySelector('#sin-resultados').hidden = filtradas.length > 0;
    document.querySelector('#elegir-rancho').disabled = filtradas.length === 0;
  });
}
document.querySelectorAll('[data-password]').forEach(c => c.addEventListener('change', () => {
  document.getElementById(c.dataset.password).type = c.checked ? 'text' : 'password';
}));
