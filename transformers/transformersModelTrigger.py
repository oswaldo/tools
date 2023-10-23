#!/usr/bin/env python3.10

# Very prototype-y version just to experiment with an initial "interface" with the other oztools scripts.
# Only tested to somewhat working with calls like:
# python transformers/transformersModelTrigger.py --model_name 'EleutherAI/gpt-neo-125M' --revision=main --cache_dir ~/oztools/transformersCache/EleutherAI/gpt-neo-125M --pad_token='<|endoftext|>' --max_length 1000 --input_text 'how do I create a main method in Java?'
# The output is not really usable yet, but it's a start.

import transformers
import argparse


def generate_text(model_name, revision, cache_dir, pad_token, max_length, input_text):
    try:
        model = transformers.AutoModelForCausalLM.from_pretrained(
            model_name,
            cache_dir=cache_dir,
            revision=revision,
        )
        tokenizer = transformers.AutoTokenizer.from_pretrained(
            model_name,
            cache_dir=cache_dir,
            revision=revision,
        )
        tokenizer.pad_token = pad_token
    except Exception as e:
        print(f"Error loading model or tokenizer: {e}")
        return
    input = "You asked: " + input_text
    inputs = tokenizer.encode(
        input, return_tensors='pt', padding='longest')
    outputs = model.generate(
        inputs,
        pad_token_id=tokenizer.eos_token_id,
        max_length=max_length,
        repetition_penalty=20.0,
        temperature=0.5,
        # do_sample=True,
        # top_p=0.92,
    )
    generated_text = tokenizer.decode(
        outputs[0]).replace(input, "").replace(pad_token, "").strip()
    print(generated_text)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_name", type=str, required=True)
    parser.add_argument("--revision", type=str, required=True)
    parser.add_argument("--cache_dir", type=str, required=True)
    parser.add_argument("--pad_token", type=str, required=True)
    parser.add_argument("--max_length", type=int, required=True)
    parser.add_argument("--input_text", type=str, required=True)
    args = parser.parse_args()

    generate_text(
        args.model_name, args.revision, args.cache_dir, args.pad_token, args.max_length, args.input_text
    )
