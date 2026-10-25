export const RECOVERY_QUESTIONS=[
 'What was the name of your first pet?',
 'What is a memorable place from your childhood?',
 'What was your childhood nickname?'
];
export function recoveryMaterial(answers){
 if(!Array.isArray(answers)||answers.length!==3||answers.some(x=>typeof x!=='string'||x.length>128))throw Object.assign(new Error('Answer all three security questions using 4–128 characters each'),{status:400});
 const normalized=answers.map(x=>x.normalize('NFKC').trim().replace(/\s+/gu,' ').toLowerCase());
 if(normalized.some(x=>x.length<4)||new Set(normalized).size!==3)throw Object.assign(new Error('Use three different answers of at least 4 characters'),{status:400});
 return 'vert-recovery-v1:'+JSON.stringify(normalized);
}
