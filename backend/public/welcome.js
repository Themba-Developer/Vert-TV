const activate=document.querySelector('#activate'),motion=document.querySelector('#motion');
activate.addEventListener('click',()=>{location.href='/signup'});
motion.addEventListener('click',()=>{const paused=document.body.classList.toggle('motion-paused');motion.setAttribute('aria-pressed',String(paused));motion.textContent=paused?'Play background ▷':'Pause background Ⅱ'});
if(matchMedia('(prefers-reduced-motion: reduce)').matches)motion.hidden=true;
