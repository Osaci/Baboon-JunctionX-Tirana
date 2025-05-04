import pandas as pd

# Create the Q&A data
qa_data = {
    'Question': [
        'How long is the delivery time?',
        'What is the estimated delivery time?',
        'When will my order arrive?',
        'How do I track my order?',
        'Where is my order?',
        'Can I track my delivery?',
        'Is my order ready?',
        'Has my order been prepared?',
        'When will my food be ready?',
        'How do I cancel my order?',
        'Can I cancel my order?',
        'I want to cancel my order',
        'What payment methods do you accept?',
        'How can I pay?',
        'Do you accept credit cards?',
        'What is your refund policy?',
        'How do I get a refund?',
        'Can I get my money back?',
        'Is there a minimum order amount?',
        'What is the minimum order?',
        'Minimum order value?',
        'Do you deliver to my area?',
        'What areas do you deliver to?',
        'Delivery zones?',
        'How much is the delivery fee?',
        'What are the delivery charges?',
        'Delivery cost?',
        'Can I change my delivery address?',
        'How do I update my address?',
        'Change delivery location?'
    ],
    'Answer': [
        'Our standard delivery time is 30-45 minutes depending on your location and restaurant distance.',
        'Delivery typically takes 30-45 minutes. You can track your order in real-time through our app.',
        'Your order will arrive within 30-45 minutes. Track it in real-time on our app.',
        'You can track your order in real-time through our app. Go to "My Orders" and select your current order.',
        'Track your order status in the "My Orders" section of our app. You\'ll see real-time updates.',
        'Yes! Real-time tracking is available in the "My Orders" section of our app.',
        'You can check your order status in the app. Look for the "Order Status" in your current order details.',
        'Check the "My Orders" section to see if your order is being prepared or is ready for delivery.',
        'Preparation time varies by restaurant. Check your order status in the app for real-time updates.',
        'You can cancel your order within 5 minutes of placing it. Go to "My Orders" and select "Cancel Order".',
        'Orders can be cancelled within 5 minutes of placement. Find the cancel option in "My Orders".',
        'To cancel, go to "My Orders" within 5 minutes of ordering and select the cancel option.',
        'We accept credit cards, debit cards, digital wallets (Apple Pay, Google Pay), and cash on delivery.',
        'Payment options include credit/debit cards, digital wallets, and cash on delivery.',
        'Yes, we accept all major credit cards including Visa, Mastercard, and American Express.',
        'Refunds are processed within 5-7 business days for cancelled orders or quality issues.',
        'For refunds, contact our support team. Refunds take 5-7 business days to process.',
        'Yes, refunds are available for cancelled orders and quality issues. Processing takes 5-7 business days.',
        'Minimum order amount varies by restaurant, typically $10-15. Check each restaurant\'s page for details.',
        'Each restaurant sets its own minimum order value, usually between $10-15.',
        'Minimum order requirements are shown on each restaurant\'s page, typically $10-15.',
        'Enter your address in the app to check delivery availability. We cover most areas within the city.',
        'We deliver to most areas within the city limits. Enter your address to check availability.',
        'Our delivery zones cover the city and surrounding suburbs. Check availability by entering your address.',
        'Delivery fees range from $2-5 depending on distance. The exact fee is shown at checkout.',
        'Delivery charges vary by distance, typically $2-5. You\'ll see the exact amount before checkout.',
        'Delivery costs $2-5 based on distance. The fee is displayed during checkout.',
        'You can change your delivery address before the restaurant starts preparing your order.',
        'To update your address, contact support immediately if your order hasn\'t been prepared yet.',
        'Address changes are possible before food preparation begins. Contact support for assistance.'
    ]
}

# Create DataFrame and save to Excel
df = pd.DataFrame(qa_data)
df.to_excel('questions_and_answers.xlsx', index=False)