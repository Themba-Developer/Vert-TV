const modal=document.querySelector('#activation'),activate=document.querySelector('#activate'),close=document.querySelector('#close'),motion=document.querySelector('#motion');
activate.addEventListener('click',()=>{modal.showModal();close.focus()});
close.addEventListener('click',()=>modal.close());
modal.addEventListener('click',e=>{if(e.target===modal){const r=modal.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)modal.close()}});
modal.addEventListener('close',()=>activate.focus());
motion.addEventListener('click',()=>{const paused=document.body.classList.toggle('motion-paused');motion.setAttribute('aria-pressed',String(paused));motion.textContent=paused?'Play background ▷':'Pause background Ⅱ'});
if(matchMedia('(prefers-reduced-motion: reduce)').matches)motion.hidden=true;
